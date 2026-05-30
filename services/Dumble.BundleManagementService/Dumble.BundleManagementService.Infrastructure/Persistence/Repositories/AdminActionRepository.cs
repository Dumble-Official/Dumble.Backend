using Dumble.BundleManagementService.Application.Contracts;
using Dumble.BundleManagementService.Infrastructure.Persistence.Data;
using Dumble.BundleManagementService.Infrastructure.Persistence.Data.Models;
using Microsoft.EntityFrameworkCore;

namespace Dumble.BundleManagementService.Infrastructure.Persistence.Repositories;

internal sealed class AdminActionRepository(BundleManagementDbContext context) : IAdminActionRepository
{
    public async Task RecordAsync(string adminId, string actionType, string targetType, string targetId, string ownerId, string? details, CancellationToken ct)
    {
        context.AdminActions.Add(new AdminAction
        {
            Id = Guid.NewGuid(),
            AdminId = adminId,
            ActionType = actionType,
            TargetType = targetType,
            TargetId = targetId,
            OwnerId = ownerId,
            Details = details,
            CreatedAt = DateTime.UtcNow
        });
        await context.SaveChangesAsync(ct);
    }

    public async Task<int> CountByAdminAsync(string adminId, string actionType, TimeSpan window, CancellationToken ct)
    {
        var since = DateTime.UtcNow - window;
        return await context.AdminActions
            .CountAsync(x => x.AdminId == adminId && x.ActionType == actionType && x.CreatedAt >= since, ct);
    }
}
