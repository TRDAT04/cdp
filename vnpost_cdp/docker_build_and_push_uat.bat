@echo off

docker build -f Dockerfile -t trdat04/cdp-backend-api:2.3 .
docker push trdat04/cdp-backend-api:2.3

pause
