using Dumble.BundleManagementService.Domain.BundleAggregate.Enums;

namespace Dumble.BundleManagementService.Application.Contracts;

public sealed class User
{
    public Guid Id { get; set; }
    public string Email { get; set; } = default!;
    // public SubscriptionType SubscriptionType { get; set; } 
    public OwnerType AccountType { get; set; }
    public List<string> Roles = new();
}