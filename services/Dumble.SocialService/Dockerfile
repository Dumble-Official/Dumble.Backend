FROM mcr.microsoft.com/dotnet/aspnet:9.0 AS base
WORKDIR /app
EXPOSE 5021

FROM mcr.microsoft.com/dotnet/sdk:9.0 AS build
ARG BUILD_CONFIGURATION=Release
WORKDIR /src

# Copy SharedKernel (referenced by multiple projects)
COPY Dumble.SharedKernel/Dumble.SharedKernel/Dumble.SharedKernel.csproj Dumble.SharedKernel/Dumble.SharedKernel/

# Copy all project files for restore
COPY Dumble.SocialService/Dumble.SocialService.API/Dumble.SocialService.API.csproj Dumble.SocialService/Dumble.SocialService.API/
COPY Dumble.SocialService/Dumble.SocialService.Application/Dumble.SocialService.Application.csproj Dumble.SocialService/Dumble.SocialService.Application/
COPY Dumble.SocialService/Dumble.SocialService.Contracts/Dumble.SocialService.Contracts.csproj Dumble.SocialService/Dumble.SocialService.Contracts/
COPY Dumble.SocialService/Dumble.SocialService.Domain/Dumble.SocialService.Domain.csproj Dumble.SocialService/Dumble.SocialService.Domain/
COPY Dumble.SocialService/Dumble.SocialService.Infrastructure/Dumble.SocialService.Infrastructure.csproj Dumble.SocialService/Dumble.SocialService.Infrastructure/

RUN dotnet restore "Dumble.SocialService/Dumble.SocialService.API/Dumble.SocialService.API.csproj"

# Copy source code and build
COPY Dumble.SharedKernel/ Dumble.SharedKernel/
COPY Dumble.SocialService/ Dumble.SocialService/

WORKDIR "/src/Dumble.SocialService/Dumble.SocialService.API"
RUN dotnet build "Dumble.SocialService.API.csproj" -c $BUILD_CONFIGURATION -o /app/build

FROM build AS publish
ARG BUILD_CONFIGURATION=Release
RUN dotnet publish "Dumble.SocialService.API.csproj" -c $BUILD_CONFIGURATION -o /app/publish /p:UseAppHost=false

FROM base AS final
WORKDIR /app
COPY --from=publish /app/publish .
ENV ASPNETCORE_URLS=http://+:5021
ENTRYPOINT ["dotnet", "Dumble.SocialService.API.dll"]
