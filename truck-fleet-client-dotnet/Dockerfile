FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src
COPY proto ./proto
COPY truck-fleet-client-dotnet ./truck-fleet-client-dotnet
WORKDIR /src/truck-fleet-client-dotnet
RUN dotnet publish -c Release -o /out

FROM mcr.microsoft.com/dotnet/runtime:8.0
WORKDIR /app
COPY --from=build /out .
ENTRYPOINT ["dotnet", "truck-fleet-client-dotnet.dll"]
