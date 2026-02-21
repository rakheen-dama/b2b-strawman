FROM alpine:3.19

RUN apk add --no-cache curl jq

WORKDIR /seed

COPY wait-for-backend.sh .
COPY seed.sh .

RUN chmod +x wait-for-backend.sh seed.sh

CMD ["./seed.sh"]
