FROM mcr.microsoft.com/dotnet/aspnet:9.0 AS base
WORKDIR /app
EXPOSE 5022

FROM mcr.microsoft.com/dotnet/sdk:9.0 AS build
ARG BUILD_CONFIGURATION=Release
WORKDIR /src

# Copy SharedKernel (referenced by multiple projects)
COPY Dumble.SharedKernel/Dumble.SharedKernel/Dumble.SharedKernel.csproj Dumble.SharedKernel/Dumble.SharedKernel/

# Copy all project files for restore
COPY Dumble.ChatService/Dumble.ChatService.API/Dumble.ChatService.API.csproj Dumble.ChatService/Dumble.ChatService.API/
COPY Dumble.ChatService/Dumble.ChatService.Application/Dumble.ChatService.Application.csproj Dumble.ChatService/Dumble.ChatService.Application/
COPY Dumble.ChatService/Dumble.ChatService.Contracts/Dumble.ChatService.Contracts.csproj Dumble.ChatService/Dumble.ChatService.Contracts/
COPY Dumble.ChatService/Dumble.ChatService.Domain/Dumble.ChatService.Domain.csproj Dumble.ChatService/Dumble.ChatService.Domain/
COPY Dumble.ChatService/Dumble.ChatService.Infrastructure/Dumble.ChatService.Infrastructure.csproj Dumble.ChatService/Dumble.ChatService.Infrastructure/

RUN dotnet restore "Dumble.ChatService/Dumble.ChatService.API/Dumble.ChatService.API.csproj"

# Copy source code and build
COPY Dumble.SharedKernel/ Dumble.SharedKernel/
COPY Dumble.ChatService/ Dumble.ChatService/

WORKDIR "/src/Dumble.ChatService/Dumble.ChatService.API"
RUN dotnet build "Dumble.ChatService.API.csproj" -c $BUILD_CONFIGURATION -o /app/build

FROM build AS publish
ARG BUILD_CONFIGURATION=Release
RUN dotnet publish "Dumble.ChatService.API.csproj" -c $BUILD_CONFIGURATION -o /app/publish /p:UseAppHost=false

FROM base AS final
WORKDIR /app
COPY --from=publish /app/publish .
ENV ASPNETCORE_URLS=http://+:5022
ENTRYPOINT ["dotnet", "Dumble.ChatService.API.dll"]
