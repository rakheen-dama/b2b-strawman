FROM alpine:3.19

RUN apk add --no-cache curl jq

WORKDIR /seed

COPY wait-for-backend.sh .
COPY seed.sh .
COPY rich-seed.sh .
COPY lib/ lib/

RUN chmod +x wait-for-backend.sh seed.sh rich-seed.sh lib/*.sh

CMD ["./seed.sh"]
