# pi-Lisco
pi-Lisco is a parallel and incremental stream-based point-cloud clustering
which clusters Lidar data in streaming sliding windows, reusing the results
from overlapping portions of the data and enabling single-window (i.e., in-place) processing.
pi-Lisco employs efficient work-sharing among threads, facilitated by the ScaleGate data structure,
and embeds a customised version of the STINGER concurrent data structure.

This repository contains the implementation of pi-Lisco as well as the original Lisco in java and related experiments.

## Setup
The installation of Liebre is not covered in this setup.
From the script folder:
1. run `bash compile.sh`
2. run `bash pilisco.sh` to run experiments for pi-Lisco
3. run `bash lisco.sh` to run experiments for Lisco
