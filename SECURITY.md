# Security policy

## Reporting a vulnerability

Please report security problems privately through GitHub's private vulnerability reporting:
<https://github.com/monk-developer-tools/mockhub/security/advisories/new>

Don't open a public issue for a vulnerability. Include what you found, how to reproduce it and the version or image
digest you tested. You'll get a reply on the report.

## Supported versions

Fixes go into `main` and the `abhaypratap/mockhub:latest` Docker image. Run the latest image to get them.

## What MockHub does to protect data

See the trust centre: <https://mockhub.devops-monk.com/trust/>

## Scanner findings we've assessed

When a scanner reports a vulnerability that can't affect MockHub and has no fix yet, the reasoning is published as an
OpenVEX statement in [`.vex/`](.vex/) and attached to each published image, so Docker Scout shows it as not affected.
The image is rebuilt when the upstream fix ships.
