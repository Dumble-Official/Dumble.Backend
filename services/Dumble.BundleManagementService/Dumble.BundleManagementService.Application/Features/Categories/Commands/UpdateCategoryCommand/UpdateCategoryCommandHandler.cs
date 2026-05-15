using Dumble.BundleManagementService.Application.Contracts.Repositories;
using Dumble.BundleManagementService.Domain.CategoryAggregate;
using Dumble.BundleManagementService.Domain.CategoryAggregate.ValueObjects;
using Dumble.SharedKernel.Contracts;
using Dumble.SharedKernel.Enums;
using MediatR;

namespace Dumble.BundleManagementService.Application.Features.Categories.Commands.UpdateCategoryCommand;

public sealed class UpdateCategoryCommandHandler(
    IGenericRepository<Category, CategoryId> categoryRepository,
    ILoggedInUserService loggedInUserService)
    : IRequestHandler<UpdateCategoryCommand, UpdateCategoryCommandResponse>
{
    public async Task<UpdateCategoryCommandResponse> Handle(UpdateCategoryCommand request, CancellationToken cancellationToken)
    {
        var user = loggedInUserService.GetCurrentUser();
        if (!user.IsInRole(UserType.Admin))
            throw new UnauthorizedAccessException("Only administrators can update categories");

        var category = await categoryRepository.Get(CategoryId.Create(request.Id))
            ?? throw new KeyNotFoundException($"Category {request.Id} not found");

        category.Update(Name.Create(request.Name));

        categoryRepository.Update(category);
        await categoryRepository.CompleteAsync();

        return new UpdateCategoryCommandResponse(category.Id.Value.ToString(), category.Name.Value);
    }
}
