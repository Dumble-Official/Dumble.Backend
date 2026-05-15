FROM mcr.microsoft.com/dotnet/aspnet:9.0 AS base
WORKDIR /app
EXPOSE 5023

FROM mcr.microsoft.com/dotnet/sdk:9.0 AS build
ARG BUILD_CONFIGURATION=Release
WORKDIR /src

# Copy SharedKernel (referenced by multiple projects)
COPY Dumble.SharedKernel/Dumble.SharedKernel/Dumble.SharedKernel.csproj Dumble.SharedKernel/Dumble.SharedKernel/

# Copy all project files for restore
COPY Dumble.NotificationService/Dumble.NotificationService.API/Dumble.NotificationService.API.csproj Dumble.NotificationService/Dumble.NotificationService.API/
COPY Dumble.NotificationService/Dumble.NotificationService.Application/Dumble.NotificationService.Application.csproj Dumble.NotificationService/Dumble.NotificationService.Application/
COPY Dumble.NotificationService/Dumble.NotificationService.Contracts/Dumble.NotificationService.Contracts.csproj Dumble.NotificationService/Dumble.NotificationService.Contracts/
COPY Dumble.NotificationService/Dumble.NotificationService.Domain/Dumble.NotificationService.Domain.csproj Dumble.NotificationService/Dumble.NotificationService.Domain/
COPY Dumble.NotificationService/Dumble.NotificationService.Infrastructure/Dumble.NotificationService.Infrastructure.csproj Dumble.NotificationService/Dumble.NotificationService.Infrastructure/

RUN dotnet restore "Dumble.NotificationService/Dumble.NotificationService.API/Dumble.NotificationService.API.csproj"

# Copy source code and build
COPY Dumble.SharedKernel/ Dumble.SharedKernel/
COPY Dumble.NotificationService/ Dumble.NotificationService/

WORKDIR "/src/Dumble.NotificationService/Dumble.NotificationService.API"
RUN dotnet build "Dumble.NotificationService.API.csproj" -c $BUILD_CONFIGURATION -o /app/build

FROM build AS publish
ARG BUILD_CONFIGURATION=Release
RUN dotnet publish "Dumble.NotificationService.API.csproj" -c $BUILD_CONFIGURATION -o /app/publish /p:UseAppHost=false

FROM base AS final
WORKDIR /app
COPY --from=publish /app/publish .
ENV ASPNETCORE_URLS=http://+:5023
ENTRYPOINT ["dotnet", "Dumble.NotificationService.API.dll"]
