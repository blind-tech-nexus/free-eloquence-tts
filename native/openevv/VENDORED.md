# openevv, vendored

The engine used to be a git submodule. It is part of this repository now:
everything under `native/openevv` is committed here, built by
`native/build-native.sh`, and no `git submodule` command is needed to work on
this project.

- Upstream: <https://github.com/Mudb0y/openevv>
- Commit vendored: `9b5b801fa618b727669d8cf40ddf02af8a21d826`
- Licence: MIT, with IBM's terms for the language data and two data files;
  see `LICENSE` and `NOTICE` in this directory.

`native/patches/0001-upsample-method-override.patch` is applied to
`src/eci/sound/eci_pcm.c` in this tree. It adds `evv_setUpsampleMethod`, which
the Android front end exposes as Audio optimization. The build script still
knows how to apply it, idempotently, so an older checkout is brought forward
by the same command that builds it.

Editing the engine here is expected: this is a fork of the tree for Android,
not a read-only copy. Changes that would be useful upstream belong in a patch
under `native/patches` as well, so the reason for them is written down beside
the code.
