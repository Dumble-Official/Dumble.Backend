FROM mcr.microsoft.com/dotnet/aspnet:8.0 AS base
WORKDIR /app
EXPOSE 8080

FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
ARG BUILD_CONFIGURATION=Release
WORKDIR /src

COPY Dumble.SharedKernel/Dumble.SharedKernel/Dumble.SharedKernel.csproj Dumble.SharedKernel/Dumble.SharedKernel/
COPY Dumble.PostService/Dumble.PostService.Domain/Dumble.PostService.Domain.csproj Dumble.PostService/Dumble.PostService.Domain/
COPY Dumble.PostService/Dumble.PostService.Contracts/Dumble.PostService.Contracts.csproj Dumble.PostService/Dumble.PostService.Contracts/
COPY Dumble.PostService/Dumble.PostService.Application/Dumble.PostService.Application.csproj Dumble.PostService/Dumble.PostService.Application/
COPY Dumble.PostService/Dumble.PostService.Infrastructure/Dumble.PostService.Infrastructure.csproj Dumble.PostService/Dumble.PostService.Infrastructure/
COPY Dumble.PostService/Dumble.PostService.API/Dumble.PostService.API.csproj Dumble.PostService/Dumble.PostService.API/
RUN dotnet restore Dumble.PostService/Dumble.PostService.API/Dumble.PostService.API.csproj

COPY Dumble.SharedKernel/ Dumble.SharedKernel/
COPY Dumble.PostService/ Dumble.PostService/
WORKDIR /src/Dumble.PostService/Dumble.PostService.API
RUN dotnet build -c $BUILD_CONFIGURATION -o /app/build

FROM build AS publish
ARG BUILD_CONFIGURATION=Release
RUN dotnet publish -c $BUILD_CONFIGURATION -o /app/publish /p:UseAppHost=false

FROM base AS final
WORKDIR /app
COPY --from=publish /app/publish .
ENTRYPOINT ["dotnet", "Dumble.PostService.API.dll"]
