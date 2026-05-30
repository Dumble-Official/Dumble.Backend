namespace Dumble.BundleManagementService.Application.Contracts;

public interface IAdminActionRepository
{
    Task RecordAsync(string adminId, string actionType, string targetType, string targetId, string ownerId, string? details, CancellationToken ct);
    Task<int> CountByAdminAsync(string adminId, string actionType, TimeSpan window, CancellationToken ct);
}
