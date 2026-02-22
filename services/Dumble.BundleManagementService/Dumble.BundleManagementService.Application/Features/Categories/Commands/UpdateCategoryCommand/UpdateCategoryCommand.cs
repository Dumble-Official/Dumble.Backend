using Dumble.BundleManagementService.Domain.CategoryAggregate;
using MediatR;

namespace Dumble.BundleManagementService.Application.Features.Categories.Commands.UpdateCategoryCommand;

public sealed record UpdateCategoryCommand(Guid Id, string Name) : IRequest<UpdateCategoryCommandResponse>;