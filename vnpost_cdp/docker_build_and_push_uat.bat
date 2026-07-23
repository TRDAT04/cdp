@echo off

docker build -f Dockerfile -t trdat04/cdp-backend-api:2.5 .
docker push trdat04/cdp-backend-api:2.5

pause
