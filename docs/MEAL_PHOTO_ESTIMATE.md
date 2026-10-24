# Meal photo estimate

`POST /v1/nutrition/estimate/` takes a meal photo and answers with Claude's
estimate of the pictured portion: `name`, `grams`, `carbs`, `fat`, `protein`,
`kcal` (totals, not per 100 g), `items` (the same six fields for every food on
the plate; the totals are their sums), and the `photo_id` of the stored photo.

```json
{ "image": "<base64 JPEG>", "language": "de" }
```

Errors: `1600` the image is not a JPEG of at most 2 MB, `1601` no estimate came
back (CLI missing, token expired, usage limit, timeout after 120 s).

The estimate is a starting point. The app puts the carbohydrates into the bolus
calculator's editable carbs field, where the user checks them before the dose is
confirmed. The meal is logged with the estimate as an entry carrying `photo_id`.

## How it runs

The api container ships the Claude Code CLI. `MealPhotoEstimator` starts it per
photo, one run at a time:

```
claude -p --input-format stream-json --output-format stream-json --verbose \
  --tools WebSearch --allowedTools WebSearch --strict-mcp-config --setting-sources "" --no-session-persistence \
  --model opus --fallback-model sonnet --json-schema <schema> \
  --system-prompt <prompt>
```

- The photo goes in as a base64 image block over **stdin**, never as a file.
- **Web search is the only tool.** A photo can carry text; a prompt injected
  that way has no file, shell or fetch tool, so it cannot read `config.ini`.
  The search runs on Anthropic's side and only sees what is already in the
  conversation (the photo and the prompt), so it reaches no server secret. The
  worst an injected search can do is waste a query or skew the numbers, which
  the user checks in the bolus calculator anyway.
- `--json-schema` makes the CLI validate the answer; `MealEstimateReply` reads
  `structured_output` from the final `result` line.

The prompt asks for one item per food, counts only the edible part (a banana
without its peel), names typical German portions as anchors (a slice of bread
45 g, a medium banana 120 g) and says to take the typical portion when unsure.
Listing the foods one by one is what keeps an oversized portion from hiding in
a single total, and lets the user see which food an odd number came from.

Before the items the model writes `visible_text`: every brand, size and
nutrition value it can read on packaging. Values then come from the packaging
first, a recognised branded product second and generic tables only last. That
order exists because of a Fassbrause: recognised correctly, but priced like a
regular lemonade from a generic table, about 10 g carbohydrates too high. Drinks
are estimated by volume (the printed bottle or can size, 1 ml as 1 g). The
schema is a text block because `JSONObject` keeps no key order, and
`visible_text` only helps when it is written before the numbers.

Each item then carries `count` (pieces, 0 for rice, soup, drinks) and
`piece_grams` before `grams`. That exists because of four slices of toast,
stacked, estimated as two in photo after photo, with two coconut macaroons
miscounted beside them. With only `grams` to write the model never counted; now
the prompt makes it count each piece first (crust edges of a stack, halves,
pieces at the frame edge) and derive the weight from the count. Only food eaten piece by piece gets a count (slices, rolls, biscuits,
fruit); toppings such as cheese or butter, even in slices, take 0, so the app
offers no counter nobody needs. Item names are the plain food in one or two
words ("Toast", not "Toastbrot (Sandwichtoast, 4 Scheiben als 4 Dreiecke)");
the details stay in `visible_text`. The server
keeps `count` on every item and returns it; the app shows a −/+ counter for
counted foods, so a miscount is one tap instead of a new photo. Items stored
before the count existed read as 0.

The model is Opus, not Sonnet: it counts pieces on a photo noticeably better,
at the price of a slower run and more of the subscription's usage. Sonnet is
only the fallback when Opus is overloaded. The reasoning effort stays at the
CLI default: lowering it would be faster, but whether counting suffers is only
visible on real photos, so it waits for a measurement.

## Product lookup

When the photo shows a branded, packaged product the model can name, and the
packaging in the photo does not already show its net weight and nutrition per
100 g, it runs ONE web search for that exact product (brand, name and size plus
"nutrition weight" in one query), preferring the manufacturer or Open Food
Facts. One query instead of several, and none when the label is readable, is
what keeps the lookup's cost to one search round. Found values beat its memory; values printed on the packaging in the
photo still beat both. Unbranded food is never looked up. That exists because
piece weights from memory drift (a bar, a pack of biscuits), and a wrong piece
weight multiplies through every counted piece.

A run takes about 5 to 20 seconds, 30 to 60 with a lookup. Every timeout on the
way is sized for that: the CLI is killed after 120 s, Spring's async request
timeout is 150 s, nginx reads for 150 s, and the app waits 160 s.

## Storage

Every successful estimate is kept in `nutrition_meal_photos`: the JPEG exactly as
the app sent it (the app's camera takes it as a 720p JPEG, a few hundred
kB) and the raw estimate, before the user corrected anything: the totals as
columns and the per-food `items` as a JSON array.

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
