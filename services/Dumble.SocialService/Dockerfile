FROM mcr.microsoft.com/dotnet/aspnet:8.0 AS base
WORKDIR /app
EXPOSE 8080

FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
ARG BUILD_CONFIGURATION=Release
WORKDIR /src

COPY Dumble.SharedKernel/Dumble.SharedKernel/Dumble.SharedKernel.csproj Dumble.SharedKernel/Dumble.SharedKernel/
COPY Dumble.SocialService/Dumble.SocialService.Domain/Dumble.SocialService.Domain.csproj Dumble.SocialService/Dumble.SocialService.Domain/
COPY Dumble.SocialService/Dumble.SocialService.Contracts/Dumble.SocialService.Contracts.csproj Dumble.SocialService/Dumble.SocialService.Contracts/
COPY Dumble.SocialService/Dumble.SocialService.Application/Dumble.SocialService.Application.csproj Dumble.SocialService/Dumble.SocialService.Application/
COPY Dumble.SocialService/Dumble.SocialService.Infrastructure/Dumble.SocialService.Infrastructure.csproj Dumble.SocialService/Dumble.SocialService.Infrastructure/
COPY Dumble.SocialService/Dumble.SocialService.API/Dumble.SocialService.API.csproj Dumble.SocialService/Dumble.SocialService.API/
RUN dotnet restore Dumble.SocialService/Dumble.SocialService.API/Dumble.SocialService.API.csproj

COPY Dumble.SharedKernel/ Dumble.SharedKernel/
COPY Dumble.SocialService/ Dumble.SocialService/
WORKDIR /src/Dumble.SocialService/Dumble.SocialService.API
RUN dotnet build -c $BUILD_CONFIGURATION -o /app/build

FROM build AS publish
ARG BUILD_CONFIGURATION=Release
RUN dotnet publish -c $BUILD_CONFIGURATION -o /app/publish /p:UseAppHost=false

FROM base AS final
WORKDIR /app
COPY --from=publish /app/publish .
ENTRYPOINT ["dotnet", "Dumble.SocialService.API.dll"]
