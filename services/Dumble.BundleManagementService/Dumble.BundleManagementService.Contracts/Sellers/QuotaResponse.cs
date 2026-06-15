namespace Dumble.BundleManagementService.Contracts.Sellers;

/// <summary>Seller bundle quota — the shape the Subscription service's BundleManagementClient binds.</summary>
public sealed record QuotaResponse(int ActiveBundleCount, int MaxAllowed, bool CanCreateMore);
