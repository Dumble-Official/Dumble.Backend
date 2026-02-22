using Dumble.BundleManagementService.Domain.CategoryAggregate;
using Dumble.BundleManagementService.Domain.CategoryAggregate.ValueObjects;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Internal;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace Dumble.BundleManagementService.Infrastructure.Persistence.Data.Configurations;

internal sealed class CategoryConfigurations : IEntityTypeConfiguration<Category>
{
    public void Configure(EntityTypeBuilder<Category> builder)
    {
        builder.ToTable("Categories");

        // Id
        builder.HasKey(b => b.Id);
        builder.Property(b => b.Id)
            .HasConversion(
                id => id.Value,
                value => CategoryId.Create(value))
            .ValueGeneratedNever()
            .IsRequired();

        // Value Objects
        builder.Property(b => b.Name)
            .HasConversion(
                name => name.Value,
                value => Name.Create(value))
            .IsRequired();
    }
}