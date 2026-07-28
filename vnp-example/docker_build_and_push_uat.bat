@echo off

docker build -f Dockerfile -t 172.20.0.70:5000/java/vnp-example-api:1.8 .
docker push 172.20.0.70:5000/java/vnp-example-api:1.8


pause
