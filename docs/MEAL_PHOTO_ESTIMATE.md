# Meal photo estimate

`POST /v1/nutrition/estimate/` takes a meal photo and answers with Claude's
estimate of the pictured portion: `name`, `grams`, `carbs`, `fat`, `protein`,
`kcal` (totals, not per 100 g), plus the `photo_id` of the stored photo.

```json
{ "image": "<base64 JPEG>", "language": "de" }
```

Errors: `1600` the image is not a JPEG of at most 2 MB, `1601` no estimate came
back (CLI missing, token expired, usage limit, timeout after 45 s).

The estimate is a starting point. The app puts the carbohydrates into the bolus
calculator's editable carbs field, where the user checks them before the dose is
confirmed. The meal is logged with the estimate as an entry carrying `photo_id`.

## How it runs

The api container ships the Claude Code CLI. `MealPhotoEstimator` starts it per
photo, one run at a time:

```
claude -p --input-format stream-json --output-format stream-json --verbose \
  --tools "" --strict-mcp-config --setting-sources "" --no-session-persistence \
  --model sonnet --json-schema <schema> --system-prompt <prompt>
```

- The photo goes in as a base64 image block over **stdin**, never as a file.
- **No tools at all.** A photo can carry text; a prompt injected that way has
  nothing to call, so it cannot read `config.ini` or reach the network.
- `--json-schema` makes the CLI validate the answer; `MealEstimateReply` reads
  `structured_output` from the final `result` line.

A run takes about 3 to 10 seconds.

## Storage

Every successful estimate is kept in `nutrition_meal_photos`: the JPEG exactly as
the app sent it (the app's camera takes it as a 720p JPEG, a few hundred
kB) and the raw estimate, before the user corrected anything.

## Opening a photo again

`POST /v1/nutrition/photos/find/` with `{ "id": "<photo_id>" }` returns
`{ "image": "<base64 JPEG>" }`. Only the user who took the photo gets it; an id of
someone else's photo answers `1602` exactly like one that does not exist.

## Sign-in token

The CLI uses a Claude subscription through a long-lived OAuth token:

1. On any machine with a browser: `claude setup-token`, sign in, copy the token.
2. On the server, next to `docker-compose.yml`, put it in `.env` (not committed):
   ```
   CLAUDE_CODE_OAUTH_TOKEN=sk-ant-oat01-...
   ```
3. `docker compose up -d --build api`.

The token lasts about a year. When estimates start failing with `1601`, run step
1 again and replace the line in `.env`, then `docker compose up -d api`.

A subscription is meant for personal use. A deployment that serves other people
should use an API key (`ANTHROPIC_API_KEY`) instead; the CLI picks that up the
same way.
