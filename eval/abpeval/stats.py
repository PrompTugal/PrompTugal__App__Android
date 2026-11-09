import math


def wilson(k, n, z=1.96):
    """95% Wilson score interval for k successes in n. Returns (lo, hi); (0, 1) when n is 0."""
    if n == 0:
        return (0.0, 1.0)
    p = k / n
    d = 1 + z * z / n
    c = p + z * z / (2 * n)
    h = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n))
    return (max(0.0, (c - h) / d), min(1.0, (c + h) / d))


def one_in(k, n):
    """Headline '1 in X' for k events in n runs, with the interval translated from the rate's CI."""
    if n == 0:
        return "n/a"
    if k == 0:
        lo, hi = wilson(0, n)
        return f"none in {n} (rate under {hi:.0%}, so rarer than 1 in {1 / hi:.1f} at most)"
    lo, hi = wilson(k, n)
    return f"1 in {n / k:.1f} (95% CI: 1 in {1 / hi:.1f} to 1 in {1 / lo:.1f})" if lo > 0 else f"1 in {n / k:.1f} (95% CI: 1 in {1 / hi:.1f} to rarer)"
