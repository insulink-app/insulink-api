# Account security

How the api protects an account, and the error codes the app and the panel
switch on.

## Guarding endpoints

`AppAuthenticationFilter` guards every handler method carrying `@AppEndpoint`.
Whether a request is guarded is decided by asking Spring's own
`RequestMappingHandlerMapping` (`EndpointRepository.routesToAnnotated`), not by
comparing the URI: the old string comparison let `/v1/glucose/%68istory/`
reach the controller unguarded. A request the mapping cannot resolve on a known
path counts as guarded (fail closed).

A token only passes while the session it names is `ACTIVE` and belongs to the
same user, so a logout or a password change ends it at once rather than when
the 10 minute token runs out. Status codes stay as the clients expect them:
417 = refresh and retry, 403 = the session is over.

## Sessions and refresh tokens

- Refresh tokens live 90 days; every refresh hands out a new one.
- Only a SHA-256 digest is stored. Rows from before that still hold the token
  itself and are accepted until their next rotation.
- The token the current one replaced stays good for one more rotation (lost
  responses, the app and its service refreshing at once). An older, properly
  signed token coming back closes the session: it is a stolen copy or one
  already traded in.
- A password change closes every other session of the account.

## Signing in

`SignInThrottle` refuses sign-ins (1003) after 5 failures for one account name
or 20 from one client address within 15 minutes. It is in memory: a restart
resets it. nginx adds a per-address rate limit on `/v1/signin/`, `/v1/signup/`
and `/v1/refresh/` for both hosts (`proxy/default.conf`). An unknown name
costs one hash check like a wrong password, so timing does not reveal which
names exist.

New passwords (sign-up, password change) need at least
`User.MINIMUM_PASSWORD_LENGTH` (10) characters; older, shorter ones still sign
in.

## The wrapped data key

`/user/key/find/` returns, `/user/key/change/` stores the user's data key,
wrapped on the device with a key derived from the password. The api treats it
as an opaque JSON object of at most 4 KB (the format is the app's, see its
`docs/POD_KEY_ENCRYPTION.md`). Storing needs the password, so a stolen session
cannot swap in its own key. `/user/password/change/` takes the key re-wrapped
for the new password in the same request, so key and password never disagree.

## Deleting an account

`/user/delete/` (password required) deletes the user and every row in every
table with a `user_id` column, in one transaction (`AccountEraser`). Tables are
found through `information_schema`, so new tables are covered without touching
the eraser. The predictor removes the model file of a user that no longer
exists on its next daily run.

## Error codes

| Code | Endpoint | Meaning |
|---|---|---|
| 1000 | sign-in | wrong name or password |
| 1003 | sign-in | too many failed attempts, try later |
| 1005 | sign-up, password change | new password shorter than 10 characters |
| 1000 | password change, key change, delete | wrong current password |
| 1001 | password change, key change | `key` is not a JSON object of at most 4 KB |
| 1006 | password change | the account holds a wrapped key and the request brings no re-wrapped one |
| 1000 | key find | the account has no key yet |
| 1603 | meal estimate | the day's 30 estimates are used up |
