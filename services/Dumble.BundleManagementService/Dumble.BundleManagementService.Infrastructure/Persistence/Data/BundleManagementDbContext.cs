using System.Reflection;
using Dumble.BundleManagementService.Domain.BundleAggregate;
using Dumble.BundleManagementService.Domain.CategoryAggregate;
using Dumble.BundleManagementService.Infrastructure.Persistence.Data.Models;
using Microsoft.EntityFrameworkCore;

namespace Dumble.BundleManagementService.Infrastructure.Persistence.Data;

public sealed class BundleManagementDbContext(DbContextOptions<BundleManagementDbContext> options) : DbContext(options)
{
    public DbSet<Bundle> Bundles { get; private set; } = default!;
    public DbSet<Category> Categories { get; private set; } = default!;
    public DbSet<AdminAction> AdminActions { get; private set; } = default!;

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.ApplyConfigurationsFromAssembly(Assembly.GetExecutingAssembly());
    }

    protected override void ConfigureConventions(ModelConfigurationBuilder configurationBuilder)
    {
        configurationBuilder.Properties<decimal>()
            .HavePrecision(18, 2);
    }
}
