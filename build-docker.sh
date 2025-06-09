#!/bin/bash -e

lein uberjar
docker build -t centripetal-indicators-service .
