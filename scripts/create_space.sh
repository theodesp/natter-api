#!/bin/bash

set -ex
curl -i -d '{"name": "'"$1"'", "owner": "'"$2"'"}' http://localhost:4567/spaces

curl -i -d "{\"name\": \"test\",\"owner\": \"'); DROP TABLE spaces; --\"}" http://localhost:4567/spaces