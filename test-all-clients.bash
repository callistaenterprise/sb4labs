#!/usr/bin/env bash

IMPL=sequential ./test-one-client.bash
IMPL=interface-client ./test-one-client.bash
IMPL=rest-client ./test-one-client.bash
