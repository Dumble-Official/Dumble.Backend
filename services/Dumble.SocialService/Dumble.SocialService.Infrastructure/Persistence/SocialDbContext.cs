using Microsoft.EntityFrameworkCore;
using Dumble.SocialService.Domain.Entities;

namespace Dumble.SocialService.Infrastructure.Persistence;

public class SocialDbContext : DbContext
{
    public SocialDbContext(DbContextOptions<SocialDbContext> options) : base(options) { }

    public DbSet<Follow> Follows => Set<Follow>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.ApplyConfigurationsFromAssembly(typeof(SocialDbContext).Assembly);
    }
}
