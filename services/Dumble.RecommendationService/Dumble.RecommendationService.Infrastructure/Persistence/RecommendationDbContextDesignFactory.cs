using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Design;

namespace Dumble.RecommendationService.Infrastructure.Persistence;

/// <summary>
/// Lets the EF CLI build the context at design time (migrations) without booting
/// the web host — the host requires JWT_SECRET and would otherwise apply migrations
/// on startup. The connection string only needs to be a valid Npgsql string for
/// scaffolding; it is not connected to while generating a migration.
/// </summary>
public sealed class RecommendationDbContextDesignFactory : IDesignTimeDbContextFactory<RecommendationDbContext>
{
    public RecommendationDbContext CreateDbContext(string[] args)
    {
        var connectionString = Environment.GetEnvironmentVariable("ConnectionStrings__RecommendationDb")
            ?? "Host=localhost;Port=5432;Database=dumble_recommendations;Username=dumble;Password=dumble123";

        var options = new DbContextOptionsBuilder<RecommendationDbContext>()
            .UseNpgsql(connectionString)
            .UseSnakeCaseNamingConvention()
            .Options;

        return new RecommendationDbContext(options);
    }
}
