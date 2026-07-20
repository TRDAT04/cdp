@echo off

docker build -f Dockerfile -t trdat04/cdp-backend-api:1.8 .
docker push trdat04/cdp-backend-api:1.8

pause
