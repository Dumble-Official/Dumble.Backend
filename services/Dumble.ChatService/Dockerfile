FROM mcr.microsoft.com/dotnet/aspnet:8.0 AS base
WORKDIR /app
EXPOSE 8080

FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
ARG BUILD_CONFIGURATION=Release
WORKDIR /src

COPY Dumble.SharedKernel/Dumble.SharedKernel/Dumble.SharedKernel.csproj Dumble.SharedKernel/Dumble.SharedKernel/
COPY Dumble.ChatService/Dumble.ChatService.Domain/Dumble.ChatService.Domain.csproj Dumble.ChatService/Dumble.ChatService.Domain/
COPY Dumble.ChatService/Dumble.ChatService.Contracts/Dumble.ChatService.Contracts.csproj Dumble.ChatService/Dumble.ChatService.Contracts/
COPY Dumble.ChatService/Dumble.ChatService.Application/Dumble.ChatService.Application.csproj Dumble.ChatService/Dumble.ChatService.Application/
COPY Dumble.ChatService/Dumble.ChatService.Infrastructure/Dumble.ChatService.Infrastructure.csproj Dumble.ChatService/Dumble.ChatService.Infrastructure/
COPY Dumble.ChatService/Dumble.ChatService.API/Dumble.ChatService.API.csproj Dumble.ChatService/Dumble.ChatService.API/
RUN dotnet restore Dumble.ChatService/Dumble.ChatService.API/Dumble.ChatService.API.csproj

COPY Dumble.SharedKernel/ Dumble.SharedKernel/
COPY Dumble.ChatService/ Dumble.ChatService/
WORKDIR /src/Dumble.ChatService/Dumble.ChatService.API
RUN dotnet build -c $BUILD_CONFIGURATION -o /app/build

FROM build AS publish
ARG BUILD_CONFIGURATION=Release
RUN dotnet publish -c $BUILD_CONFIGURATION -o /app/publish /p:UseAppHost=false

FROM base AS final
WORKDIR /app
COPY --from=publish /app/publish .
ENTRYPOINT ["dotnet", "Dumble.ChatService.API.dll"]
