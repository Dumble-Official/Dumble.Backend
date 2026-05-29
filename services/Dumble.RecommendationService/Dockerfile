FROM mcr.microsoft.com/dotnet/aspnet:9.0 AS base
WORKDIR /app
EXPOSE 5024

FROM mcr.microsoft.com/dotnet/sdk:9.0 AS build
ARG BUILD_CONFIGURATION=Release
WORKDIR /src

# Copy SharedKernel (referenced by the Application + Infrastructure projects)
COPY Dumble.SharedKernel/Dumble.SharedKernel/Dumble.SharedKernel.csproj Dumble.SharedKernel/Dumble.SharedKernel/

# Copy all project files for restore
COPY Dumble.RecommendationService/Dumble.RecommendationService.API/Dumble.RecommendationService.API.csproj Dumble.RecommendationService/Dumble.RecommendationService.API/
COPY Dumble.RecommendationService/Dumble.RecommendationService.Application/Dumble.RecommendationService.Application.csproj Dumble.RecommendationService/Dumble.RecommendationService.Application/
COPY Dumble.RecommendationService/Dumble.RecommendationService.Contracts/Dumble.RecommendationService.Contracts.csproj Dumble.RecommendationService/Dumble.RecommendationService.Contracts/
COPY Dumble.RecommendationService/Dumble.RecommendationService.Domain/Dumble.RecommendationService.Domain.csproj Dumble.RecommendationService/Dumble.RecommendationService.Domain/
COPY Dumble.RecommendationService/Dumble.RecommendationService.Infrastructure/Dumble.RecommendationService.Infrastructure.csproj Dumble.RecommendationService/Dumble.RecommendationService.Infrastructure/

RUN dotnet restore "Dumble.RecommendationService/Dumble.RecommendationService.API/Dumble.RecommendationService.API.csproj"

# Copy source code and build
COPY Dumble.SharedKernel/ Dumble.SharedKernel/
COPY Dumble.RecommendationService/ Dumble.RecommendationService/

WORKDIR "/src/Dumble.RecommendationService/Dumble.RecommendationService.API"
RUN dotnet build "Dumble.RecommendationService.API.csproj" -c $BUILD_CONFIGURATION -o /app/build

FROM build AS publish
ARG BUILD_CONFIGURATION=Release
RUN dotnet publish "Dumble.RecommendationService.API.csproj" -c $BUILD_CONFIGURATION -o /app/publish /p:UseAppHost=false

FROM base AS final
WORKDIR /app
COPY --from=publish /app/publish .
ENV ASPNETCORE_URLS=http://+:5024
ENTRYPOINT ["dotnet", "Dumble.RecommendationService.API.dll"]
