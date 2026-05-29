using Dumble.RecommendationService.Domain.Outbox;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace Dumble.RecommendationService.Infrastructure.Persistence.Configurations;

internal sealed class OutboxInteractionConfiguration : IEntityTypeConfiguration<OutboxInteraction>
{
    public void Configure(EntityTypeBuilder<OutboxInteraction> builder)
    {
        builder.ToTable("interaction_outbox");

        builder.HasKey(x => x.Id);

        builder.Property(x => x.UserId).IsRequired().HasMaxLength(64);
        builder.Property(x => x.ItemId).IsRequired().HasMaxLength(64);

        // Enums persisted as readable strings rather than opaque ints.
        builder.Property(x => x.Operation).IsRequired().HasConversion<string>().HasMaxLength(32);
        builder.Property(x => x.Status).IsRequired().HasConversion<string>().HasMaxLength(16);

        builder.Property(x => x.RatingValue);
        builder.Property(x => x.DurationSeconds);
        builder.Property(x => x.OccurredAt).IsRequired();
        builder.Property(x => x.SourceEventId).HasMaxLength(128);
        builder.Property(x => x.Attempts).IsRequired();
        builder.Property(x => x.CreatedAt).IsRequired();

        // The flush worker drains pending rows oldest-first with FOR UPDATE SKIP LOCKED;
        // this composite index keeps that scan cheap as the table grows and is reaped.
        builder.HasIndex(x => new { x.Status, x.Id })
            .HasDatabaseName("ix_interaction_outbox_status_id");

        // Idempotent Channel-2 consumption: a redelivered integration event cannot
        // insert a second row. Channel-1 signals carry no source event id (NULL), and
        // Postgres permits unlimited NULLs in a unique index, so the filter keeps the
        // guarantee scoped to evented rows only.
        builder.HasIndex(x => x.SourceEventId)
            .IsUnique()
            .HasFilter("source_event_id IS NOT NULL")
            .HasDatabaseName("ux_interaction_outbox_source_event_id");
    }
}
