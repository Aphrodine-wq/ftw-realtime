# Build stage
FROM hexpm/elixir:1.19.5-erlang-28.4.1-alpine-3.21.3 AS build

RUN apk add --no-cache build-base git

WORKDIR /app

ENV MIX_ENV=prod

# Install hex + rebar
RUN mix local.hex --force && mix local.rebar --force

# Install deps first (cached layer)
COPY mix.exs mix.lock ./
RUN mix deps.get --only prod
RUN mix deps.compile

# Copy app source
COPY config config
COPY lib lib
COPY priv priv
COPY assets assets

# Build assets and release
RUN mix assets.deploy
RUN mix compile
RUN mix release

# Runtime stage — minimal Alpine image
FROM alpine:3.21.3 AS runtime

RUN apk add --no-cache libstdc++ openssl ncurses-libs

WORKDIR /app

COPY --from=build /app/_build/prod/rel/ftw_realtime ./

ENV PHX_SERVER=true
ENV MIX_ENV=prod

CMD ["bin/ftw_realtime", "start"]
