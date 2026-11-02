# Glucose share links

A user can hand someone a link that shows their glucose and nothing else. The
link opens the panel's public page `panel.insulink.de/share/<id>` (installable
as a PWA). Links are managed from the app and the panel.

## Endpoints

| Route | Auth | What |
|---|---|---|
| `POST /glucose/share/create/` `{name}` | session | new link; `name` says whom it is for (max 64, sanitized) |
| `GET /glucose/share/find/` | session | the caller's links, newest first: `{shares:[{id,name,created_at}]}` |
| `POST /glucose/share/delete/` `{share_id}` | session | revokes; 1000 unknown, 1001 not the caller's |
| `GET /share/<id>/` | **none** | `{entries:[{value,time}]}`, last 24 h, mg/dL + epoch ms, oldest first; 1000 for an unknown or malformed id |

## Why it is shaped like this

- **The id is the credential.** It is a random UUID (122 random bits from
  `SecureRandom`), so it cannot be guessed or enumerated, and no session is
  involved. Anyone holding the link sees the glucose until it is deleted.
- **Revocation is a delete.** No flags, no expiry: a deleted row answers 1000
  on the very next poll.
- **Glucose plus a greeting.** The public reply carries values and times, the
  link's name and the owner's account name (for "Hello Mum, Lukas's glucose").
  No settings or thresholds, so the page colours against the fixed 70 to 180
  range. The account name is also the sign-in name, so a link holder learns it.
- **24 h window.** Enough for a follower's chart, and it bounds what a leaked
  link exposes.
- **Account deletion** needs nothing extra: `AccountEraser` finds
  `glucose_shares` through its `user_id` column like every other table.
