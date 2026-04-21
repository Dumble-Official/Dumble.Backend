FROM mcr.microsoft.com/dotnet/aspnet:8.0 AS base
WORKDIR /app
EXPOSE 8080

FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
ARG BUILD_CONFIGURATION=Release
WORKDIR /src

COPY Dumble.SharedKernel/Dumble.SharedKernel/Dumble.SharedKernel.csproj Dumble.SharedKernel/Dumble.SharedKernel/
COPY Dumble.NotificationService/Dumble.NotificationService.Domain/Dumble.NotificationService.Domain.csproj Dumble.NotificationService/Dumble.NotificationService.Domain/
COPY Dumble.NotificationService/Dumble.NotificationService.Contracts/Dumble.NotificationService.Contracts.csproj Dumble.NotificationService/Dumble.NotificationService.Contracts/
COPY Dumble.NotificationService/Dumble.NotificationService.Application/Dumble.NotificationService.Application.csproj Dumble.NotificationService/Dumble.NotificationService.Application/
COPY Dumble.NotificationService/Dumble.NotificationService.Infrastructure/Dumble.NotificationService.Infrastructure.csproj Dumble.NotificationService/Dumble.NotificationService.Infrastructure/
COPY Dumble.NotificationService/Dumble.NotificationService.API/Dumble.NotificationService.API.csproj Dumble.NotificationService/Dumble.NotificationService.API/
RUN dotnet restore Dumble.NotificationService/Dumble.NotificationService.API/Dumble.NotificationService.API.csproj

COPY Dumble.SharedKernel/ Dumble.SharedKernel/
COPY Dumble.NotificationService/ Dumble.NotificationService/
WORKDIR /src/Dumble.NotificationService/Dumble.NotificationService.API
RUN dotnet build -c $BUILD_CONFIGURATION -o /app/build

FROM build AS publish
ARG BUILD_CONFIGURATION=Release
RUN dotnet publish -c $BUILD_CONFIGURATION -o /app/publish /p:UseAppHost=false

FROM base AS final
WORKDIR /app
COPY --from=publish /app/publish .
ENTRYPOINT ["dotnet", "Dumble.NotificationService.API.dll"]
