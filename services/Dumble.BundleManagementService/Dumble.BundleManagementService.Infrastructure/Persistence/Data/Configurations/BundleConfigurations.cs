using Dumble.BundleManagementService.Domain.BundleAggregate;
using Dumble.BundleManagementService.Domain.BundleAggregate.Enums;
using Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;
using Dumble.BundleManagementService.Domain.CategoryAggregate.ValueObjects;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Name = Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects.Name;

namespace Dumble.BundleManagementService.Infrastructure.Persistence.Data.Configurations;

internal sealed class BundleConfigurations : IEntityTypeConfiguration<Bundle>
{
    public void Configure(EntityTypeBuilder<Bundle> builder)
    {
        builder.ToTable("Bundles");

        // Id
        builder.HasKey(b => b.Id);
        builder.Property(b => b.Id)
            .HasConversion(
                id => id.Value,
                value => BundleId.Create(value))
            .ValueGeneratedNever()
            .IsRequired();

        // Value Objects
        builder.Property(b => b.Name)
            .HasConversion(
                name => name.Value,
                value => Name.Create(value))
            .IsRequired();

        builder.Property(b => b.Description)
            .HasConversion(
                description => description.Value,
                value => Description.Create(value))
            .IsRequired();

        builder.Property(b => b.Price)
            .HasConversion(
                price => price.Value,
                value => Price.Create(value))
            .IsRequired();

        builder.Property(b => b.CategoryId)
            .HasConversion(
                categoryId => categoryId.Value,
                value => CategoryId.Create(value))
            .IsRequired();

        // Enums
        builder.Property(b => b.Status)
            .HasConversion(
                status => status.ToString(),
                value => Enum.Parse<Status>(value))
            .IsRequired();

        builder.Property(b => b.OwnerType)
            .HasConversion(
                ownerType => ownerType.ToString(),
                value => Enum.Parse<OwnerType>(value))
            .IsRequired();

        // Owned collections: Images
        builder.OwnsMany<BundleImage>(b => b.Images, imagesBuilder =>
        {
            imagesBuilder.WithOwner().HasForeignKey("BundleId");
            imagesBuilder.Property<int>("Id");
            imagesBuilder.HasKey("Id", "BundleId");
            imagesBuilder.Property(i => i.Value).HasColumnName("ImageUrl").IsRequired();
            imagesBuilder.ToTable("BundleImages");
        });

        builder.OwnsMany<AccountId>(b => b.Viewers, viewersBuilder =>
        {
            viewersBuilder.WithOwner().HasForeignKey("BundleId");
            viewersBuilder.Property<int>("Id");
            viewersBuilder.HasKey("Id", "BundleId");
            viewersBuilder.Property(v => v.Value).HasColumnName("ViewerId").IsRequired();
            viewersBuilder.ToTable("BundleViewers");
        });

        // Dates
        builder.Property(b => b.CreatedOn).IsRequired();
        builder.Property(b => b.LastModifiedOn).IsRequired();
        builder.Property(b => b.ExpiresOn).IsRequired();

        // Owner / Creator
        builder.Property(b => b.OwnerId)
            .HasConversion(
                ownerId => ownerId.Value,
                value => AccountId.Create(value))
            .IsRequired();

        builder.Property(b => b.CreatedBy)
            .HasConversion(
                createdBy => createdBy.Value,
                value => AccountId.Create(value))
            .IsRequired();

        builder.Property(b => b.LastModifiedBy)
            .HasConversion(
                modifiedBy => modifiedBy.Value,
                value => AccountId.Create(value))
            .IsRequired();
        
        
        builder.Metadata.FindNavigation(nameof(Bundle.Viewers))!
            .SetPropertyAccessMode(PropertyAccessMode.Field);
        
        
        builder.Metadata.FindNavigation(nameof(Bundle.Images))!
            .SetPropertyAccessMode(PropertyAccessMode.Field);
    }
}