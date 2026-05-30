FROM mcr.microsoft.com/dotnet/aspnet:9.0 AS base
WORKDIR /app
EXPOSE 5134

FROM mcr.microsoft.com/dotnet/sdk:9.0 AS build
ARG BUILD_CONFIGURATION=Release
WORKDIR /src

# Copy SharedKernel (referenced by multiple projects)
COPY Dumble.SharedKernel/Dumble.SharedKernel/Dumble.SharedKernel.csproj Dumble.SharedKernel/Dumble.SharedKernel/

# Copy all project files for restore
COPY Dumble.PostService/Dumble.PostService.API/Dumble.PostService.API.csproj Dumble.PostService/Dumble.PostService.API/
COPY Dumble.PostService/Dumble.PostService.Application/Dumble.PostService.Application.csproj Dumble.PostService/Dumble.PostService.Application/
COPY Dumble.PostService/Dumble.PostService.Contracts/Dumble.PostService.Contracts.csproj Dumble.PostService/Dumble.PostService.Contracts/
COPY Dumble.PostService/Dumble.PostService.Domain/Dumble.PostService.Domain.csproj Dumble.PostService/Dumble.PostService.Domain/
COPY Dumble.PostService/Dumble.PostService.Infrastructure/Dumble.PostService.Infrastructure.csproj Dumble.PostService/Dumble.PostService.Infrastructure/

RUN dotnet restore "Dumble.PostService/Dumble.PostService.API/Dumble.PostService.API.csproj"

# Copy source code and build
COPY Dumble.SharedKernel/ Dumble.SharedKernel/
COPY Dumble.PostService/ Dumble.PostService/

WORKDIR "/src/Dumble.PostService/Dumble.PostService.API"
RUN dotnet build "Dumble.PostService.API.csproj" -c $BUILD_CONFIGURATION -o /app/build

FROM build AS publish
ARG BUILD_CONFIGURATION=Release
RUN dotnet publish "Dumble.PostService.API.csproj" -c $BUILD_CONFIGURATION -o /app/publish /p:UseAppHost=false

FROM base AS final
WORKDIR /app
COPY --from=publish /app/publish .
ENV ASPNETCORE_URLS=http://+:5134
# The app targets net8 but the base image ships the net9 runtime; roll forward so it
# runs on the installed major version (matches the recommendation service).
ENV DOTNET_ROLL_FORWARD=Major
ENTRYPOINT ["dotnet", "Dumble.PostService.API.dll"]
