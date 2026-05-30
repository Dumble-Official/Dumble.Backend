using Dumble.RecommendationService.Domain.Outbox;
using Microsoft.EntityFrameworkCore;

namespace Dumble.RecommendationService.Infrastructure.Persistence;

public sealed class RecommendationDbContext : DbContext
{
    public RecommendationDbContext(DbContextOptions<RecommendationDbContext> options) : base(options)
    {
    }

    public DbSet<OutboxInteraction> OutboxInteractions => Set<OutboxInteraction>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.ApplyConfigurationsFromAssembly(typeof(RecommendationDbContext).Assembly);
    }
}
