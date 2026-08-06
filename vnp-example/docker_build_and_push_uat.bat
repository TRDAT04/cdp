@echo off

docker build -f Dockerfile -t trdat04/cdp-backend-api:2.7 .
docker push trdat04/cdp-backend-api:2.7

pause
