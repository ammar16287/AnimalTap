"""
Regenerates the animal sounds in app/src/main/res/raw/ (synthesized originals, no licensing issues).

Technique: source-filter voice synthesis, like a tiny speech synthesizer.
  - Band-limited additive glottal source (no aliasing) following a smooth pitch contour,
    with natural jitter/shimmer, vibrato, and optional sub-harmonic roughness (growl).
  - Every harmonic is weighted by a time-varying cascade of formant resonances, so vowels
    glide ("m-ee-ow", "m-oo", "b-aa") the way real animal calls do.
  - Breath/aspiration noise, pitch-synchronously pulsed and shaped by the same formants.
  - Gentle high-pass (phone speakers), short room reverb, soft limiter and loudness matching.

Usage:  python tools/generate_sounds.py      (needs numpy + scipy)
Output: 44.1 kHz, 16-bit, mono WAV.
"""
import os
import numpy as np
from scipy import signal
from scipy.io import wavfile

SR = 44100
RNG = np.random.default_rng(7)
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "raw")


# ---------------------------------------------------------------- helpers

def tline(dur):
    return np.arange(int(dur * SR)) / SR


def contour(points, t, smooth_ms=15.0):
    """Piecewise-linear contour through (time, value) points, then smoothed."""
    ts, vs = zip(*points)
    y = np.interp(t, ts, vs)
    n = max(1, int(smooth_ms * SR / 1000))
    if n > 1:
        k = np.hanning(n * 2 + 1)
        k /= k.sum()
        y = np.convolve(np.pad(y, n, mode="edge"), k, mode="same")[n:-n]
    return y


def slow_noise(t, rate_hz, seed_scale=1.0):
    """Smooth random wobble (zero mean, ~unit std) varying at roughly rate_hz."""
    n = len(t)
    pts = max(4, int(t[-1] * rate_hz) + 4)
    knots = RNG.standard_normal(pts) * seed_scale
    return np.interp(np.linspace(0, pts - 3, n), np.arange(pts), knots)


def formant_gain(freq, formants):
    """Magnitude of a cascade of 2nd-order resonators (unity at DC).
    freq: array; formants: list of (F array, B array, extra gain)."""
    g = np.ones_like(freq)
    for F, B, gain in formants:
        g *= gain * F * F / np.sqrt((F * F - freq * freq) ** 2 + (B * freq) ** 2 + 1e-9)
    return g


def interp_formants(fpts, t):
    """fpts: list of (time, [(F, B), ...]) -> list of (F(t), B(t), 1.0)."""
    nform = len(fpts[0][1])
    out = []
    for i in range(nform):
        F = contour([(tt, fs[i][0]) for tt, fs in fpts], t, 25)
        B = contour([(tt, fs[i][1]) for tt, fs in fpts], t, 25)
        out.append((F, B, 1.0))
    return out


def voice(dur, f0_pts, formant_pts, amp_pts, tilt=1.0, jitter=0.01, shimmer=0.05,
          breath=0.1, sub=0.0, vib=(5.0, 0.0), trem=(0.0, 0.0), noise_pulse=0.6):
    t = tline(dur)
    f0 = contour(f0_pts, t)
    f0 = f0 * (1 + jitter * slow_noise(t, 40)) * (1 + vib[1] * np.sin(2 * np.pi * vib[0] * t))
    amp = contour(amp_pts, t, 8)
    amp = amp * (1 + shimmer * slow_noise(t, 30))
    if trem[1]:
        amp = amp * (1 - trem[1] * 0.5 * (1 - np.cos(2 * np.pi * trem[0] * t)))
    amp = np.clip(amp, 0, None)

    forms = interp_formants(formant_pts, t)
    phase = 2 * np.pi * np.cumsum(f0) / SR

    out = np.zeros_like(t)
    kmax = int((SR / 2 - 1000) / f0.min())
    for k in range(1, kmax + 1):
        fk = k * f0
        a = formant_gain(fk, forms) / k ** tilt
        a = np.where(fk < SR / 2 - 1000, a, 0.0)
        out += a * np.sin(k * phase + RNG.uniform(0, 0.3))
    if sub > 0:  # period-doubling roughness (growl / grunt)
        for k in range(1, 2 * kmax + 1, 2):
            fk = k * f0 / 2
            a = formant_gain(fk, forms) / (k / 2) ** tilt
            a = np.where(fk < SR / 2 - 1000, a, 0.0)
            out += sub * a * np.sin(k * phase / 2 + RNG.uniform(0, 6.28))
    out /= np.max(np.abs(out)) + 1e-9

    if breath > 0:
        nz = RNG.standard_normal(len(t))
        # aspiration is louder during the open phase of each glottal cycle
        nz *= (1 - noise_pulse) + noise_pulse * (0.5 + 0.5 * np.cos(phase)) ** 2
        out += breath * shape_noise(nz, t, forms)

    return out * amp


def shape_noise(nz, t, forms):
    """Filter noise through the time-varying formant envelope (STFT domain)."""
    nper = 1024
    f, ft, Z = signal.stft(nz, SR, nperseg=nper, noverlap=nper * 3 // 4)
    idx = np.clip((ft * SR).astype(int), 0, len(t) - 1)
    env = np.empty((len(f), len(ft)))
    for j, i in enumerate(idx):
        env[:, j] = formant_gain(f, [(F[i], B[i], g) for F, B, g in forms])
    _, y = signal.istft(Z * env, SR, nperseg=nper, noverlap=nper * 3 // 4)
    y = y[: len(t)]
    if len(y) < len(t):
        y = np.pad(y, (0, len(t) - len(y)))
    return y / (np.max(np.abs(y)) + 1e-9)


def silence(dur):
    return np.zeros(int(dur * SR))


def concat(*parts):
    return np.concatenate(parts)


def reverb(x, wet=0.12, length=0.35, decay=0.07):
    t = tline(length)
    ir = RNG.standard_normal(len(t)) * np.exp(-t / decay)
    b, a = signal.butter(2, 5000 / (SR / 2))  # darker tail
    ir = signal.lfilter(b, a, ir)
    ir /= np.sqrt(np.sum(ir ** 2))
    tail = signal.fftconvolve(np.concatenate([x, np.zeros(len(t))]), ir)[: len(x) + len(t)]
    dry = np.concatenate([x, np.zeros(len(t))])
    return dry + wet * tail


def finish(x, target_rms_db=-12.0, hp=90):
    b, a = signal.butter(2, hp / (SR / 2), "high")
    x = signal.filtfilt(b, a, x)
    x = reverb(x)
    # trim the inaudible tail
    env = np.abs(x)
    last = np.nonzero(env > env.max() * 0.002)[0][-1]
    x = x[: min(len(x), last + int(0.03 * SR))]
    # loudness match on the active part, then soft limit to -1 dBFS
    active = x[np.abs(x) > 0.05 * np.abs(x).max()]
    rms = np.sqrt(np.mean(active ** 2))
    x = x * (10 ** (target_rms_db / 20) / rms)
    ceiling = 10 ** (-1 / 20)
    x = ceiling * np.tanh(x / ceiling)
    fade = int(0.004 * SR)
    x[:fade] *= np.linspace(0, 1, fade)
    x[-fade * 4:] *= np.linspace(1, 0, fade * 4)
    return x


def save(name, x):
    pcm = np.int16(np.clip(x, -1, 1) * 32767)
    wavfile.write(os.path.join(OUT, name + ".wav"), SR, pcm)
    print(f"{name:8s} {len(x) / SR:.2f}s")


# ---------------------------------------------------------------- animals

def cow():
    d = 1.45
    return voice(
        d,
        f0_pts=[(0, 112), (0.35, 132), (0.9, 124), (d, 92)],
        formant_pts=[(0, [(260, 80), (900, 200), (2300, 300), (3300, 400)]),     # "m"
                     (0.18, [(300, 90), (850, 150), (2300, 250), (3300, 400)]),
                     (0.4, [(430, 100), (760, 120), (2400, 250), (3300, 400)]),  # "oo"
                     (1.1, [(460, 110), (800, 130), (2450, 250), (3300, 400)]),
                     (d, [(380, 120), (700, 150), (2400, 300), (3300, 400)])],
        amp_pts=[(0, 0), (0.05, 0.35), (0.2, 0.75), (0.45, 1.0), (1.05, 0.9), (d, 0)],
        tilt=1.05, jitter=0.012, shimmer=0.06, breath=0.12, sub=0.12, vib=(4.5, 0.008))


def dog():
    def bark(f, dur=0.2):
        return voice(
            dur,
            f0_pts=[(0, 380 * f), (0.035, 540 * f), (0.09, 470 * f), (dur, 300 * f)],
            formant_pts=[(0, [(320, 120), (800, 200), (2400, 300), (3500, 400)]),    # "w"
                         (0.04, [(700, 160), (1250, 200), (2700, 300), (3600, 400)]),  # "oo/a"
                         (dur, [(480, 180), (950, 220), (2500, 300), (3500, 400)])],   # "f"
            amp_pts=[(0, 0), (0.008, 1.0), (0.05, 0.85), (0.12, 0.45), (dur, 0)],
            tilt=0.75, jitter=0.03, shimmer=0.12, breath=0.4, sub=0.25, noise_pulse=0.4)
    return concat(bark(1.0), silence(0.1), bark(0.93, 0.22))


def cat():
    d = 0.95
    return voice(
        d,
        f0_pts=[(0, 470), (0.3, 700), (0.55, 650), (d, 480)],
        formant_pts=[(0, [(280, 80), (1200, 200), (2900, 300), (4200, 400)]),      # "m"
                     (0.12, [(380, 90), (2200, 150), (3100, 250), (4300, 400)]),   # "ee"
                     (0.42, [(900, 130), (1650, 150), (2900, 250), (4200, 400)]),  # "a"
                     (0.7, [(650, 120), (1100, 150), (2800, 250), (4100, 400)]),
                     (d, [(500, 130), (850, 160), (2700, 300), (4000, 400)])],     # "ow"
        amp_pts=[(0, 0), (0.06, 0.4), (0.2, 0.9), (0.45, 1.0), (0.75, 0.7), (d, 0)],
        tilt=1.0, jitter=0.008, shimmer=0.04, breath=0.08, vib=(6.0, 0.012))


def duck():
    def quack(f, dur=0.24):
        return voice(
            dur,
            f0_pts=[(0, 260 * f), (0.05, 300 * f), (dur, 230 * f)],
            formant_pts=[(0, [(500, 150), (1100, 150), (2600, 250), (3600, 400)]),   # "qu"
                         (0.05, [(850, 140), (1550, 140), (2800, 250), (3700, 400)]),  # "a"
                         (dur, [(700, 160), (1700, 180), (2700, 280), (3700, 400)])],  # "ck"
            amp_pts=[(0, 0), (0.012, 0.9), (0.06, 1.0), (0.17, 0.7), (dur, 0)],
            tilt=0.45, jitter=0.03, shimmer=0.1, breath=0.18, sub=0.45)
    return concat(quack(1.0), silence(0.08), quack(0.96, 0.26))


def sheep():
    d = 1.05
    return voice(
        d,
        f0_pts=[(0, 290), (0.15, 330), (0.6, 320), (d, 275)],
        formant_pts=[(0, [(300, 100), (900, 200), (2500, 300), (3500, 400)]),     # "b"
                     (0.08, [(800, 120), (1250, 140), (2650, 250), (3600, 400)]),  # "aa"
                     (0.8, [(760, 120), (1400, 140), (2700, 250), (3600, 400)]),
                     (d, [(600, 140), (1500, 160), (2600, 300), (3500, 400)])],    # "eh"
        amp_pts=[(0, 0), (0.03, 0.6), (0.12, 1.0), (0.75, 0.85), (d, 0)],
        tilt=0.9, jitter=0.015, shimmer=0.05, breath=0.14, sub=0.08,
        vib=(7.5, 0.035), trem=(7.5, 0.55))   # the bleat "warble"


def pig():
    def oink(f, dur=0.22):
        return voice(
            dur,
            f0_pts=[(0, 170 * f), (0.06, 230 * f), (dur, 150 * f)],
            formant_pts=[(0, [(450, 120), (900, 150), (2300, 300), (3300, 400)]),    # "o"
                         (0.08, [(500, 120), (1300, 160), (2500, 280), (3400, 400)]),  # "i"
                         (dur, [(300, 100), (1600, 220), (2500, 300), (3400, 400)])],  # "nk"
            amp_pts=[(0, 0), (0.015, 0.8), (0.07, 1.0), (0.16, 0.55), (dur, 0)],
            tilt=0.7, jitter=0.05, shimmer=0.15, breath=0.3, sub=0.6, noise_pulse=0.8)
    return concat(oink(1.0), silence(0.09), oink(1.08, 0.24))


def lion():
    d = 1.7
    return voice(
        d,
        f0_pts=[(0, 95), (0.35, 165), (0.8, 140), (1.3, 110), (d, 75)],
        formant_pts=[(0, [(400, 150), (800, 200), (2200, 300), (3200, 400)]),     # "r"
                     (0.3, [(650, 180), (1050, 200), (2400, 300), (3300, 400)]),  # "aw"
                     (1.2, [(600, 180), (950, 200), (2300, 300), (3200, 400)]),
                     (d, [(450, 200), (800, 220), (2200, 300), (3200, 400)])],
        amp_pts=[(0, 0), (0.15, 0.5), (0.4, 1.0), (1.0, 0.85), (1.45, 0.4), (d, 0)],
        tilt=0.8, jitter=0.05, shimmer=0.2, breath=0.45, sub=0.6, noise_pulse=0.5,
        trem=(28.0, 0.35))   # throat flutter growl


def bird():
    def chirp(f_start, f_peak, f_end, dur):
        t = tline(dur)
        f = contour([(0, f_start), (dur * 0.35, f_peak), (dur, f_end)], t, 4)
        f = f * (1 + 0.02 * np.sin(2 * np.pi * 55 * t))   # syrinx trill
        ph = 2 * np.pi * np.cumsum(f) / SR
        env = np.sin(np.pi * np.clip(t / dur, 0, 1)) ** 1.5
        return env * (np.sin(ph) + 0.12 * np.sin(2 * ph + 0.4) + 0.03 * np.sin(3 * ph))
    return concat(
        chirp(2600, 4300, 3800, 0.085), silence(0.045),
        chirp(2700, 4500, 4000, 0.08), silence(0.06),
        chirp(3200, 5200, 3000, 0.14), silence(0.04),
        chirp(3600, 4800, 2800, 0.09))


def frog():
    def syll(dur, fpts, f0):
        x = voice(dur, f0_pts=[(0, f0), (dur, f0 * 0.9)], formant_pts=fpts,
                  amp_pts=[(0, 0), (0.01, 1), (dur * 0.7, 0.8), (dur, 0)],
                  tilt=0.55, jitter=0.03, shimmer=0.1, breath=0.15, sub=0.3)
        t = tline(dur)
        pulses = (0.5 + 0.5 * np.cos(2 * np.pi * 32 * t)) ** 3   # pulsed croak
        return x * (0.15 + 0.85 * pulses)
    rib = syll(0.2, [(0, [(350, 100), (1500, 200), (2400, 300), (3400, 400)]),
                     (0.2, [(420, 110), (1900, 180), (2600, 300), (3500, 400)])], 110)
    bit = syll(0.17, [(0, [(380, 100), (2000, 180), (2700, 300), (3500, 400)]),
                      (0.17, [(300, 100), (1700, 200), (2500, 300), (3400, 400)])], 125)
    return concat(rib, silence(0.07), bit)


def leopard():
    """Rasping "sawing" call: alternating harsh out-breath / in-breath grunts."""
    def saw(f0, dur, gain):
        return gain * voice(
            dur,
            f0_pts=[(0, f0 * 0.9), (dur * 0.3, f0 * 1.1), (dur, f0 * 0.8)],
            formant_pts=[(0, [(550, 250), (1000, 300), (2300, 400), (3200, 500)]),
                         (dur, [(480, 250), (900, 300), (2200, 400), (3200, 500)])],
            amp_pts=[(0, 0), (0.03, 1.0), (dur * 0.6, 0.7), (dur, 0)],
            tilt=0.7, jitter=0.08, shimmer=0.3, breath=0.9, sub=0.7, noise_pulse=0.7,
            trem=(35.0, 0.4))
    return concat(saw(85, 0.26, 1.0), silence(0.07), saw(105, 0.18, 0.6), silence(0.1),
                  saw(80, 0.26, 1.0), silence(0.07), saw(100, 0.18, 0.6), silence(0.1),
                  saw(78, 0.28, 0.95))


def elephant():
    """Brassy trumpet blast: bright, rough, rising then falling."""
    d = 1.3
    return voice(
        d,
        f0_pts=[(0, 380), (0.15, 620), (0.5, 680), (0.9, 640), (d, 500)],
        formant_pts=[(0, [(500, 150), (1200, 200), (2500, 250), (3500, 400)]),
                     (0.15, [(750, 150), (1450, 180), (2600, 250), (3600, 400)]),
                     (d, [(650, 160), (1300, 200), (2500, 260), (3500, 400)])],
        amp_pts=[(0, 0), (0.06, 0.8), (0.2, 1.0), (1.0, 0.9), (d, 0)],
        tilt=0.5, jitter=0.02, shimmer=0.08, breath=0.2, sub=0.25, vib=(9.0, 0.02))


def rhino():
    """Two nasal snorts followed by a low grunt."""
    nasal = [(300, 200), (1000, 300), (2200, 400), (3500, 600)]

    def snort(dur):
        return voice(
            dur, f0_pts=[(0, 95), (dur, 80)], formant_pts=[(0, nasal), (dur, nasal)],
            amp_pts=[(0, 0), (0.02, 1.0), (dur * 0.5, 0.6), (dur, 0)],
            tilt=0.9, jitter=0.08, shimmer=0.3, breath=1.5, sub=0.5, noise_pulse=0.3)
    grunt = voice(
        0.45,
        f0_pts=[(0, 90), (0.15, 120), (0.45, 70)],
        formant_pts=[(0, [(500, 150), (900, 180), (2300, 300), (3200, 400)]),
                     (0.45, [(420, 160), (800, 200), (2200, 300), (3200, 400)])],
        amp_pts=[(0, 0), (0.03, 1.0), (0.3, 0.7), (0.45, 0)],
        tilt=0.8, jitter=0.05, shimmer=0.2, breath=0.3, sub=0.6, trem=(22.0, 0.3))
    return concat(snort(0.18), silence(0.08), snort(0.22), silence(0.12), grunt)


def monkey():
    """Excited "ooh ooh aah aah" hoots climbing in pitch."""
    oo = [(350, 90), (800, 120), (2400, 250), (3400, 400)]
    aa = [(1000, 130), (1600, 150), (2900, 250), (3900, 400)]

    def hoot(f0a, f0b, v0, v1, dur, sub=0.0):
        return voice(
            dur,
            f0_pts=[(0, f0a), (dur * 0.6, f0b), (dur, f0b * 0.92)],
            formant_pts=[(0, v0), (dur * 0.5, v1), (dur, v1)],
            amp_pts=[(0, 0), (0.03, 1.0), (dur * 0.7, 0.8), (dur, 0)],
            tilt=0.8, jitter=0.02, shimmer=0.06, breath=0.15, sub=sub, vib=(6.0, 0.01))
    return concat(hoot(330, 420, oo, oo, 0.2), silence(0.07),
                  hoot(380, 480, oo, oo, 0.2), silence(0.07),
                  hoot(500, 700, oo, aa, 0.24, 0.15), silence(0.05),
                  hoot(620, 820, aa, aa, 0.26, 0.2))


def eagle():
    """High piercing "kleee" followed by a descending chatter."""
    high = [(2500, 400), (4000, 500), (6000, 700), (8000, 900)]

    def kee(f, dur):
        return voice(
            dur,
            f0_pts=[(0, f * 0.85), (dur * 0.25, f * 1.05), (dur, f * 0.8)],
            formant_pts=[(0, high), (dur, high)],
            amp_pts=[(0, 0), (0.01, 1.0), (dur * 0.6, 0.8), (dur, 0)],
            tilt=0.9, jitter=0.02, shimmer=0.08, breath=0.25, sub=0.2)
    parts = [kee(2300, 0.32), silence(0.06)]
    for i in range(5):
        parts += [kee(2200 - i * 120, 0.09), silence(0.04)]
    return concat(*parts)


def tiger():
    """Rumbling growl that bursts into a deep "AAUURGH" roar."""
    d = 1.9
    return voice(
        d,
        f0_pts=[(0, 70), (0.25, 85), (0.5, 150), (0.9, 130), (1.4, 95), (d, 65)],
        formant_pts=[(0, [(350, 180), (700, 220), (2100, 300), (3100, 400)]),     # "rr"
                     (0.35, [(400, 180), (750, 220), (2150, 300), (3100, 400)]),
                     (0.6, [(750, 200), (1150, 220), (2500, 300), (3300, 400)]),  # "aa"
                     (1.4, [(500, 200), (850, 220), (2300, 300), (3200, 400)]),   # "oo"
                     (d, [(450, 200), (800, 220), (2200, 300), (3200, 400)])],
        amp_pts=[(0, 0), (0.1, 0.35), (0.35, 0.45), (0.6, 1.0), (1.2, 0.85), (1.6, 0.4), (d, 0)],
        tilt=0.75, jitter=0.06, shimmer=0.25, breath=0.5, sub=0.7, noise_pulse=0.5,
        trem=(24.0, 0.4))


if __name__ == "__main__":
    loud = {"bird": -13.5, "lion": -11.5, "eagle": -14.0, "tiger": -11.5}
    for name, fn in [("cow", cow), ("dog", dog), ("cat", cat), ("duck", duck), ("sheep", sheep),
                     ("pig", pig), ("lion", lion), ("bird", bird), ("frog", frog),
                     # New animals go last so the RNG sequence (and the sounds above) stay unchanged.
                     ("leopard", leopard), ("elephant", elephant), ("rhino", rhino),
                     ("monkey", monkey), ("eagle", eagle), ("tiger", tiger)]:
        save(name, finish(fn(), target_rms_db=loud.get(name, -12.0)))
