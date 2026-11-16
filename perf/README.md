# StreamVault perf baseline

Backend must be running, with at least one scanned movie.

    node perf-test.mjs run baseline      # before changes
    node perf-test.mjs run after-keyframes   # after changes
    node perf-test.mjs compare baseline after-keyframes

Optional env: SV_BASE, SV_EMAIL, SV_PASSWORD, SV_MOVIE, SV_CONC, SV_REQS, SV_WARMUP_SEC.
Takes about 1 minute (20 s of it is the encode warm-up). Close other heavy apps and run twice
to confirm numbers are stable before trusting a comparison.
