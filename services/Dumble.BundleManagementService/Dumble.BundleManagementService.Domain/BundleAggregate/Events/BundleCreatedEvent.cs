using Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;
using Dumble.BundleManagementService.Domain.Common;

namespace Dumble.BundleManagementService.Domain.BundleAggregate.Events;

public sealed record BundleCreatedEvent(BundleId BundleId) : IDomainEvent;