::: tip Don't have Haybarn yet?
[Haybarn](https://github.com/Query-farm-haybarn) is Query Farm's DuckDB-derived engine; it ships the
`vgi` extension in its community channel. Run its shell with whichever tool you
already have — no separate install step:

```bash
npx haybarn@rc      # via Node (the @rc tag is the current release)
uvx haybarn-cli     # via uv (install: curl -LsSf https://astral.sh/uv/install.sh | sh)
```

Inside the shell, enable the extension once per session:

```sql
INSTALL vgi FROM community;
LOAD vgi;
```

The `vgi` extension currently ships for Haybarn; a DuckDB release is on the way,
and a worker you write now will work with it unchanged.
:::
