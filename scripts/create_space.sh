#!/bin/bash

set -ex
curl -i -d '{"name": "'"$1"'", "owner": "'"$2"'"}' http://localhost:4567/spaces

curl -i -d "{\"name\": \"test\",\"owner\": \"'); DROP TABLE spaces; --\"}" http://localhost:4567/spaces

curl -d '{"name":"test", "owner":"a really long username that is more than 30 characters long"}' http://localhost:4567/spaces