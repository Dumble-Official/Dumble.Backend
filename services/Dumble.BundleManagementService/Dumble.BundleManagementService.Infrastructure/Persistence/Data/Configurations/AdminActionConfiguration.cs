using Dumble.BundleManagementService.Infrastructure.Persistence.Data.Models;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace Dumble.BundleManagementService.Infrastructure.Persistence.Data.Configurations;

public class AdminActionConfiguration : IEntityTypeConfiguration<AdminAction>
{
    public void Configure(EntityTypeBuilder<AdminAction> builder)
    {
        builder.ToTable("AdminActions");

        builder.HasKey(x => x.Id);

        builder.Property(x => x.AdminId).HasMaxLength(100).IsRequired();
        builder.Property(x => x.ActionType).HasMaxLength(50).IsRequired();
        builder.Property(x => x.TargetType).HasMaxLength(50).IsRequired();
        builder.Property(x => x.TargetId).HasMaxLength(100).IsRequired();
        builder.Property(x => x.OwnerId).HasMaxLength(100).IsRequired();
        builder.Property(x => x.Details).HasMaxLength(2000);

        builder.HasIndex(x => x.AdminId);
        builder.HasIndex(x => x.ActionType);
        builder.HasIndex(x => x.CreatedAt);
    }
}
