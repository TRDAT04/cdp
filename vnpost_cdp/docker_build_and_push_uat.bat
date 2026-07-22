@echo off

docker build -f Dockerfile -t trdat04/cdp-backend-api:1.10 .
docker push trdat04/cdp-backend-api:1.10

pause
