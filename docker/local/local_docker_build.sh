#!/usr/bin/env bash

cp /Users/stanleysj/.m2/repository/org/mock-server/mockserver-netty/5.11.4-SNAPSHOT/mockserver-netty-5.11.4-SNAPSHOT-jar-with-dependencies.jar ./mockserver-netty-jar-with-dependencies.jar
docker build --no-cache -t ayatacommerce/mockserver:local-snapshot .
