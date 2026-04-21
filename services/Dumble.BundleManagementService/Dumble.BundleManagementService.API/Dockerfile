FROM mcr.microsoft.com/dotnet/aspnet:9.0 AS base
WORKDIR /app
EXPOSE 8080

FROM mcr.microsoft.com/dotnet/sdk:9.0 AS build
ARG BUILD_CONFIGURATION=Release
WORKDIR /src

COPY Dumble.BundleManagementService.Domain/Dumble.BundleManagementService.Domain.csproj Dumble.BundleManagementService.Domain/
COPY Dumble.BundleManagementService.Contracts/Dumble.BundleManagementService.Contracts.csproj Dumble.BundleManagementService.Contracts/
COPY Dumble.BundleManagementService.Application/Dumble.BundleManagementService.Application.csproj Dumble.BundleManagementService.Application/
COPY Dumble.BundleManagementService.Infrastructure/Dumble.BundleManagementService.Infrastructure.csproj Dumble.BundleManagementService.Infrastructure/
COPY Dumble.BundleManagementService.API/Dumble.BundleManagementService.API.csproj Dumble.BundleManagementService.API/
RUN dotnet restore Dumble.BundleManagementService.API/Dumble.BundleManagementService.API.csproj

COPY . .
WORKDIR /src/Dumble.BundleManagementService.API
RUN dotnet build -c $BUILD_CONFIGURATION -o /app/build

FROM build AS publish
ARG BUILD_CONFIGURATION=Release
RUN dotnet publish -c $BUILD_CONFIGURATION -o /app/publish /p:UseAppHost=false

FROM base AS final
WORKDIR /app
COPY --from=publish /app/publish .
ENTRYPOINT ["dotnet", "Dumble.BundleManagementService.API.dll"]
