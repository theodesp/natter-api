#!/bin/bash

set -ex

curl -i -H 'Content-Type: application/json' -d '{"username": "'"$1"'", "password": "'"$2"'"}' http://localhost:4567/users

curl -u demo:password -d '{"name":"test space","owner":"demo"}' -H 'Content-Type: application/json' http://localhost:4567/spaces

