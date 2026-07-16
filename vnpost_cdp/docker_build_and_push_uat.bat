@echo off

docker build -f Dockerfile -t trdat04/cdp-backend-api:1.4 .
docker push trdat04/cdp-backend-api:1.4

pause
