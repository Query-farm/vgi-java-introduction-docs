# The examples moved

The runnable example workers that used to live in `examples/` are now part of
the SDK repo, at **`vgi-java/examples/docs/`**.

They moved because they are the source of truth for the documentation at
[query.farm/vgi/docs/java](https://query.farm/vgi/docs/java/), and living beside
the SDK means they build against it at HEAD — a breaking API change now shows up
as a failed build in `vgi-java` rather than as stale code in a reader's editor.
While they were here they had drifted twenty-five releases behind the published
artifact without anything noticing.

Run them from the SDK checkout:

```bash
cd vgi-java
./gradlew :examples:docs:installDist
HAYBARN=/path/to/haybarn examples/docs/verify.sh
```
