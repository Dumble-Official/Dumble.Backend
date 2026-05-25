using System.ComponentModel.DataAnnotations;

namespace Dumble.BundleManagementService.Infrastructure.Persistence.Data.Models;

public class AdminAction
{
    [Key]
    public Guid Id { get; set; }
    public string AdminId { get; set; } = string.Empty;
    public string ActionType { get; set; } = string.Empty;
    public string TargetType { get; set; } = string.Empty;
    public string TargetId { get; set; } = string.Empty;
    public string OwnerId { get; set; } = string.Empty;
    public string? Details { get; set; }
    public DateTime CreatedAt { get; set; }
}
