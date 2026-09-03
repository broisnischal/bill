# Changesets

Every change that reaches a shop is described here before it ships.

A changeset is one markdown file naming the packages it touches, the size of the bump,
and a line that ends up in the changelog and, for the Android app, on the update screen a
shopkeeper reads. Add one with `bun run change`.

The packages are `bill`, which is the Worker and the web app, and `@bill/android`, which
is the till app. A change to both gets one changeset naming both.

For `@bill/android` the bump size is also the update policy:

- **patch** and **minor** are offered. The app shows a sheet the shopkeeper can put off,
  and stops asking about that version once they do.
- **major** is forced. `minimumVersionCode` moves up to the new build, and every older
  install is held on the update screen until it takes it. Reserve it for a change the
  server will not accept the old bill format for; a shop held there cannot sell.
